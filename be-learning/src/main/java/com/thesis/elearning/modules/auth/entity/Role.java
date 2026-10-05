package com.thesis.elearning.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Authority granted to an account, mapped onto the pre-existing {@code roles}
 * table.
 *
 * <p>{@link #roleCode} stores the Spring Security authority verbatim
 * ({@code ROLE_ADMIN}, {@code ROLE_TEACHER}, {@code ROLE_STUDENT}). The client
 * switches on exactly these strings in {@code types/auth.ts}, so the value must
 * never be localised or abbreviated.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Integer roleId;

    /** Spring Security authority, e.g. {@code ROLE_STUDENT}. */
    @Column(name = "role_code", nullable = false, length = 30, unique = true)
    private String roleCode;

    /** Human-readable Vietnamese label for admin screens. */
    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;
}